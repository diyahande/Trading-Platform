import { ApiProperty } from '@nestjs/swagger';

export class ErrorResponseDto {
  @ApiProperty({ enum: ['AUTH-401', 'AUTH-409', 'VAL-422'] })
  errorCode!: 'AUTH-401' | 'AUTH-409' | 'VAL-422';

  @ApiProperty()
  message!: string;
}
